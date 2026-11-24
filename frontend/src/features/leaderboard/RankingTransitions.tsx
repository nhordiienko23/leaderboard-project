import { Component, createRef, type ReactNode } from 'react'
import type { LeaderboardEntryDTO } from '../../api/types'

interface RankingTransitionsProps {
  entries: LeaderboardEntryDTO[]
  animateChanges: boolean
  showPodium: boolean
  children: ReactNode
}

interface Position {
  left: number
  top: number
  points: number
}

type Snapshot = Map<number, Position> | null

export function sameRankings(previous: LeaderboardEntryDTO[], next: LeaderboardEntryDTO[]) {
  return previous.length === next.length && previous.every((entry, index) =>
    entry.userId === next[index].userId && entry.points === next[index].points,
  )
}

// The pre-mutation snapshot also tracks users moving between the podium and list.
export class RankingTransitions extends Component<RankingTransitionsProps, object, Snapshot> {
  private root = createRef<HTMLDivElement>()
  private motionPreference: MediaQueryList | undefined
  private animations = new Set<Animation>()

  componentDidMount() {
    this.motionPreference = window.matchMedia?.('(prefers-reduced-motion: reduce)')
    this.motionPreference?.addEventListener('change', this.handleMotionChange)
  }

  componentWillUnmount() {
    this.motionPreference?.removeEventListener('change', this.handleMotionChange)
    this.cancelAnimations()
  }

  private handleMotionChange = () => {
    if (this.motionPreference?.matches) this.cancelAnimations()
  }

  private cancelAnimations() {
    for (const animation of this.animations) animation.cancel()
    this.animations.clear()
  }

  getSnapshotBeforeUpdate(previous: RankingTransitionsProps): Snapshot {
    if (
      !this.props.animateChanges ||
      previous.showPodium !== this.props.showPodium ||
      sameRankings(previous.entries, this.props.entries) ||
      this.motionPreference?.matches ||
      typeof this.root.current?.animate !== 'function'
    ) return null

    this.cancelAnimations()
    const points = new Map(previous.entries.map((entry) => [entry.userId, entry.points]))
    const snapshot = new Map<number, Position>()
    this.root.current?.querySelectorAll<HTMLElement>('[data-ranking-user]').forEach((element) => {
      const userId = Number(element.dataset.rankingUser)
      const rect = element.getBoundingClientRect()
      const previousPoints = points.get(userId)
      if (previousPoints !== undefined) {
        snapshot.set(userId, { left: rect.left, top: rect.top, points: previousPoints })
      }
    })
    return snapshot
  }

  componentDidUpdate(_previous: RankingTransitionsProps, _state: object, snapshot: Snapshot) {
    if (!snapshot) {
      if (!this.props.animateChanges) this.cancelAnimations()
      return
    }

    const points = new Map(this.props.entries.map((entry) => [entry.userId, entry.points]))
    const elements = Array.from(this.root.current?.querySelectorAll<HTMLElement>('[data-ranking-user]') ?? [])
    // Read every final position before starting animations to avoid interleaved layout work.
    const positions = elements.map((element) => element.getBoundingClientRect())
    elements.forEach((element, index) => {
      const userId = Number(element.dataset.rankingUser)
      const previous = snapshot.get(userId)
      if (!previous) {
        this.play(element, [{ opacity: 0 }, { opacity: 1 }])
        return
      }

      const dx = previous.left - positions[index].left
      const dy = previous.top - positions[index].top
      if (dx !== 0 || dy !== 0) {
        this.play(element, [
          { transform: `translate(${dx}px, ${dy}px)` },
          { transform: 'translate(0px, 0px)' },
        ])
      }
      if (previous.points !== points.get(userId)) {
        const label = element.querySelector<HTMLElement>('[data-ranking-points]')
        if (label) this.play(label, [{ opacity: 0.55 }, { opacity: 1 }])
      }
    })
  }

  private play(element: HTMLElement, keyframes: Keyframe[]) {
    const animation = element.animate(keyframes, {
      duration: 300,
      easing: 'cubic-bezier(0.2, 0, 0, 1)',
    })
    this.animations.add(animation)
    animation.onfinish = animation.oncancel = () => { this.animations.delete(animation) }
  }

  render() {
    return <div ref={this.root} className="space-y-5">{this.props.children}</div>
  }
}
