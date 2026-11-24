export type UserRole = 'USER' | 'ADMIN';

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

export interface LoginRequest {
  login: string;
  password: string;
}

export interface RefreshTokenRequest {
  refreshToken: string;
}

export interface LogoutRequest {
  refreshToken: string;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface UserResponse {
  id: number;
  username: string;
  email: string;
  role: UserRole;
  registeredAt: string;
  lastLoginAt: string | null;
  updatedAt: string;
}

export interface UserSummaryResponse {
  id: number;
  username: string;
}

export interface PostDTO {
  id: number;
  content: string;
  userId: number;
  createdAt: string;
  updatedAt: string;
}

export interface CommentDTO {
  id: number;
  content: string;
  userId: number;
  postId: number;
  createdAt: string;
  updatedAt: string;
}

export interface LikeDTO {
  id: number;
  userId: number;
  postId: number | null;
  commentId: number | null;
  createdAt: string;
}

export interface PostEngagementResponse {
  likeCount: number;
  commentCount: number;
  likedByMe: boolean | null;
}

export interface ProfileActivityResponse {
  postsCreated: number;
  commentsAuthored: number;
  postLikesGiven: number;
  commentLikesGiven: number;
  postLikesReceived: number;
  commentLikesReceived: number;
}

export interface CommentEngagementResponse {
  likeCount: number;
  likedByMe: boolean | null;
}

export interface LeaderboardEntryDTO {
  userId: number;
  username: string;
  points: number;
}

export interface PageResponse<T> {
  content: T[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface SpringPageDto<T> {
  content: T[];
  page: {
    size: number;
    number: number;
    totalElements: number;
    totalPages: number;
  };
}

export interface FlatPageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface ContentRequest {
  content: string;
}

export interface UpdateUserRequest {
  username?: string;
  email?: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface AdminCreateUserRequest {
  username: string;
  email: string;
  password: string;
  role: UserRole;
}

export interface AdminSetPasswordRequest {
  newPassword: string;
}

export interface UpdateUserRoleRequest {
  role: UserRole;
}

export interface SpringPageParams {
  page?: number;
  size?: number;
  sort?: string | string[];
}
