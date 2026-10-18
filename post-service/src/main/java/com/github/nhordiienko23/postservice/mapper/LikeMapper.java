package com.github.nhordiienko23.postservice.mapper;

import com.github.nhordiienko23.postservice.dto.LikeDTO;
import com.github.nhordiienko23.postservice.entity.Like;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface LikeMapper {

    @Mapping(
            target = "postId",
            expression = """
                    java(like.getPost() != null
                            ? like.getPost().getId()
                            : like.getComment().getPost().getId())
                    """
    )
    @Mapping(
            target = "commentId",
            expression = """
                    java(like.getComment() != null
                            ? like.getComment().getId()
                            : null)
                    """
    )
    LikeDTO toDTO(Like like);
}