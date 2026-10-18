package com.github.nhordiienko23.postservice.mapper;

import com.github.nhordiienko23.postservice.dto.CommentDTO;
import com.github.nhordiienko23.postservice.entity.Comment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CommentMapper {

    @Mapping(target = "postId", source = "post.id")
    CommentDTO toDTO(Comment comment);
}