package org.wang.mianshi.service;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Interview Question Cache Service
 * Demonstrates a practical use case for Redis in the interview module
 */
@Service
public class InterviewQuestionCacheService {

    @Autowired
    private RedisService redisService;

    private static final String QUESTION_CACHE_KEY_PREFIX = "interview:question:";
    private static final long QUESTION_CACHE_TTL = 30; // minutes

    /**
     * Cache an interview question
     */
    public void cacheQuestion(String questionId, InterviewQuestion question) {
        String key = QUESTION_CACHE_KEY_PREFIX + questionId;
        redisService.set(key, question, QUESTION_CACHE_TTL, TimeUnit.MINUTES);
    }

    /**
     * Get cached interview question
     */
    public InterviewQuestion getCachedQuestion(String questionId) {
        String key = QUESTION_CACHE_KEY_PREFIX + questionId;
        Object obj = redisService.get(key);
        return obj instanceof InterviewQuestion ? (InterviewQuestion) obj : null;
    }

    /**
     * Check if question is cached
     */
    public boolean isQuestionCached(String questionId) {
        String key = QUESTION_CACHE_KEY_PREFIX + questionId;
        return redisService.hasKey(key);
    }

    /**
     * Remove cached question
     */
    public void removeCachedQuestion(String questionId) {
        String key = QUESTION_CACHE_KEY_PREFIX + questionId;
        redisService.delete(key);
    }

    /**
     * Cache interview question category list
     */
    public void cacheCategoryQuestions(String category, java.util.List<String> questionIds) {
        String key = "interview:category:" + category;
        for (String questionId : questionIds) {
            redisService.rightPush(key, questionId);
        }
        redisService.expire(key, 1, TimeUnit.HOURS);
    }

    /**
     * Get cached category questions
     */
    public java.util.List<Object> getCategoryQuestions(String category) {
        String key = "interview:category:" + category;
        return redisService.listRange(key, 0, -1);
    }

    /**
     * Track question view count
     */
    public long incrementQuestionViews(String questionId) {
        String key = QUESTION_CACHE_KEY_PREFIX + "views:" + questionId;
        return redisService.increment(key, 1);
    }

    /**
     * Get question view count
     */
    public long getQuestionViews(String questionId) {
        String key = QUESTION_CACHE_KEY_PREFIX + "views:" + questionId;
        Object views = redisService.get(key);
        return views != null ? ((Number) views).longValue() : 0;
    }

    /**
     * Interview Question Model
     */
    @Data
    @NoArgsConstructor
    public static class InterviewQuestion {
        private String id;
        private String title;
        private String content;
        private String category;
        private String difficulty;
        private java.util.List<String> tags;
        private String answer;
        private long createdAt;
    }
}
