package com.forestry.aireviewer.controller;

import com.forestry.aireviewer.service.HistoricalReviewPairService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReviewCaseController.class)
class ReviewCaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HistoricalReviewPairService historicalReviewPairService;

    @Test
    @DisplayName("upload pair requires beforeFile")
    void uploadPair_missingBeforeFile_returnsBadRequest() throws Exception {
        MockMultipartFile after = new MockMultipartFile("afterFile", "reviewed.txt", "text/plain", "body".getBytes());

        mockMvc.perform(multipart("/api/review-cases/upload-pair").file(after))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("upload pair requires afterFile")
    void uploadPair_missingAfterFile_returnsBadRequest() throws Exception {
        MockMultipartFile before = new MockMultipartFile("beforeFile", "draft.txt", "text/plain", "body".getBytes());

        mockMvc.perform(multipart("/api/review-cases/upload-pair").file(before))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("upload pair passes multipart fields to service")
    void uploadPair_validRequest_returnsOk() throws Exception {
        MockMultipartFile before = new MockMultipartFile("beforeFile", "draft.txt", "text/plain", "before".getBytes());
        MockMultipartFile after = new MockMultipartFile("afterFile", "reviewed.txt", "text/plain", "after".getBytes());
        when(historicalReviewPairService.ingestPair(any(), any(), eq("Wetland case"), eq("EIA")))
                .thenReturn(List.of());

        mockMvc.perform(multipart("/api/review-cases/upload-pair")
                        .file(before)
                        .file(after)
                        .param("title", "Wetland case")
                        .param("documentType", "EIA"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("upload annotated requires annotatedFile")
    void uploadAnnotated_missingFile_returnsBadRequest() throws Exception {
        mockMvc.perform(multipart("/api/review-cases/upload-annotated"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("upload notes requires notesFile")
    void uploadNotes_missingFile_returnsBadRequest() throws Exception {
        mockMvc.perform(multipart("/api/review-cases/upload-notes"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("upload notes passes multipart fields to service")
    void uploadNotes_validRequest_returnsOk() throws Exception {
        MockMultipartFile notes = new MockMultipartFile("notesFile", "notes.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "body".getBytes());
        when(historicalReviewPairService.ingestReviewerNotes(any(), eq("提交版.docx"), eq("Forest plan"), eq("Notes")))
                .thenReturn(List.of());

        mockMvc.perform(multipart("/api/review-cases/upload-notes")
                        .file(notes)
                        .param("relatedFileName", "提交版.docx")
                        .param("title", "Forest plan")
                        .param("documentType", "Notes"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("upload annotated passes multipart fields to service")
    void uploadAnnotated_validRequest_returnsOk() throws Exception {
        MockMultipartFile annotated = new MockMultipartFile("annotatedFile", "reviewed.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "body".getBytes());
        when(historicalReviewPairService.ingestAnnotated(any(), eq("Wetland EIA"), eq("EIA")))
                .thenReturn(List.of());

        mockMvc.perform(multipart("/api/review-cases/upload-annotated")
                        .file(annotated)
                        .param("title", "Wetland EIA")
                        .param("documentType", "EIA"))
                .andExpect(status().isOk());
    }
}
