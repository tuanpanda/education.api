package com.education.base.service.quiz.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** GĐ4 - cấu hình: {@code app.quiz.grace-sec} (ân hạn mạng, mặc định 30 giây - Q14). */
@Component
public class QuizSettings {

    public QuizSettings(@Value("${app.quiz.grace-sec:30}") int graceSec) {
        QuizRules.setGraceSec(graceSec);
    }
}
