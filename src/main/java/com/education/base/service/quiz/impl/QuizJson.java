package com.education.base.service.quiz.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * GĐ4 - (de)serialize các cột CLOB JSON: SNAPSHOT câu hỏi, CANDIDATE_SNAPSHOT của quy tắc, QUESTION_ORDER của attempt,
 * ACCEPTED_ANSWERS, SELECTED_OPTION_IDS. ObjectMapper riêng (không phụ thuộc cấu hình web).
 */
@Component
public class QuizJson {

    /** Ảnh chụp câu hỏi tại lúc xuất bản / bốc đề. {@code options[].correct} không bao giờ ra cổng trước khi công bố. */
    public record Snapshot(Long id, String type, String topic, Integer difficulty, String content, String explanation,
                           List<SnapOption> options, List<String> acceptedAnswers, BigDecimal points) {
        public Snapshot withPoints(BigDecimal p) {
            return new Snapshot(id, type, topic, difficulty, content, explanation, options, acceptedAnswers, p);
        }
    }

    public record SnapOption(Long id, String content, boolean correct) {
    }

    /** Thứ tự lựa chọn đã xáo của 1 câu trong attempt. */
    public record PaperItem(Long questionId, List<Long> optionOrder) {
    }

    private static final TypeReference<List<Snapshot>> SNAPSHOT_LIST = new TypeReference<>() { };
    private static final TypeReference<List<PaperItem>> PAPER = new TypeReference<>() { };
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() { };
    private static final TypeReference<List<Long>> LONGS = new TypeReference<>() { };

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public String write(Object value) {
        try {
            return value == null ? null : mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không ghi được JSON: " + e.getOriginalMessage(), e);
        }
    }

    public Snapshot snapshot(String json) {
        return read(json, new TypeReference<Snapshot>() { }, null);
    }

    public List<Snapshot> snapshots(String json) {
        return read(json, SNAPSHOT_LIST, new ArrayList<>());
    }

    public List<PaperItem> paper(String json) {
        return read(json, PAPER, new ArrayList<>());
    }

    public List<String> strings(String json) {
        return read(json, STRINGS, new ArrayList<>());
    }

    public List<Long> longs(String json) {
        return read(json, LONGS, new ArrayList<>());
    }

    private <T> T read(String json, TypeReference<T> type, T fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON không hợp lệ: " + e.getOriginalMessage(), e);
        }
    }
}
