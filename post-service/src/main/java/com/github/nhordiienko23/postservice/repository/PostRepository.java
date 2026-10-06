package com.github.nhordiienko23.postservice.repository;

import com.github.nhordiienko23.postservice.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post,Long> {
}
