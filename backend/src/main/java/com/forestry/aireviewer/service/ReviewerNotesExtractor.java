package com.forestry.aireviewer.service;

import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulls a list of review opinions out of a free-form "审核意见" document.
 * Such files are not anchored to specific paragraphs in the original report —
 * they are a flat list of items like "三、关于第 5.2 节生物多样性章节…"
 * Each item becomes one ReviewCase of type REVIEWER_NOTES downstream.
 */
@Component
public class ReviewerNotesExtractor {

    private static final Logger log = LoggerFactory.getLogger(ReviewerNotesExtractor.class);

    private static final Pattern ITEM_START = Pattern.compile(
            "^\\s*(" +
                    // 一、 二、 三、 ...
                    "[一二三四五六七八九十百千零〇]+\\s*[、\\.．:：]|" +
                    // （一） (一) 〔一〕
                    "[（\\(〔][一二三四五六七八九十百千零〇]+[）\\)〕]\\s*[、\\.．:：]?|" +
                    // 1. 1、 1) 1）
                    "\\d{1,3}\\s*[、\\.．\\)）]|" +
                    // 第 N 条 / 第N章 / 第N节
                    "第\\s*[一二三四五六七八九十百千零〇0-9]+\\s*[条章节款项部分]" +
                    ")\\s*"
    );

    private static final Pattern SECTION_REFERENCE = Pattern.compile(
            // 第 5.2 节 / 第5.2.1节 / 第五章
            "第\\s*([0-9]+(?:\\.[0-9]+){0,3})\\s*[章节款条项]" +
                    "|第\\s*([一二三四五六七八九十百千零〇]+)\\s*[章节款条项]" +
                    // 5.2 节 / 5.2.1节
                    "|(?<![0-9])([0-9]+(?:\\.[0-9]+){1,3})\\s*[章节款条项]" +
                    // Section 5.2
                    "|[Ss]ection\\s+([0-9]+(?:\\.[0-9]+){0,3})"
    );

    private final Tika tika = new Tika();

    public List<NoteItem> extract(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return List.of();
        }
        String text;
        try {
            text = tika.parseToString(file.getInputStream());
        } catch (Exception e) {
            log.warn("Failed to extract text from notes file '{}': {}",
                    file.getOriginalFilename(), e.getMessage());
            return List.of();
        }
        List<NoteItem> items = parseItems(text == null ? "" : text);
        log.info("Extracted {} reviewer note items from '{}'", items.size(), file.getOriginalFilename());
        return items;
    }

    List<NoteItem> parseItems(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String[] lines = text.split("\\R");
        List<String> rawItems = new ArrayList<>();
        StringBuilder current = null;
        for (String raw : lines) {
            String line = raw == null ? "" : raw;
            if (ITEM_START.matcher(line).find()) {
                if (current != null) {
                    rawItems.add(current.toString());
                }
                current = new StringBuilder(line);
            } else if (current != null) {
                current.append('\n').append(line);
            }
        }
        if (current != null) {
            rawItems.add(current.toString());
        }

        if (rawItems.isEmpty()) {
            // Fallback: split on blank-line paragraphs
            for (String para : text.split("\\R\\s*\\R+")) {
                if (!para.isBlank()) {
                    rawItems.add(para);
                }
            }
        }

        List<NoteItem> items = new ArrayList<>();
        for (String raw : rawItems) {
            String body = collapseWhitespace(raw);
            if (body.isBlank()) {
                continue;
            }
            items.add(new NoteItem(body, findSectionReference(body)));
        }
        return items;
    }

    private String findSectionReference(String body) {
        Matcher m = SECTION_REFERENCE.matcher(body);
        if (!m.find()) {
            return null;
        }
        return m.group().trim();
    }

    private String collapseWhitespace(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("[ \\t]+", " ").replaceAll("\\R+", "\n");
    }
}
