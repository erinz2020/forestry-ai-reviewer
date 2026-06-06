package com.forestry.aireviewer.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface CommentExtractor {

    boolean supports(MultipartFile file);

    List<ExtractedComment> extract(MultipartFile file);
}
