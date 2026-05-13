package org.example.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RAG (Retrieval-Augmented Generation) 服务
 * 结合向量检索和 DeepSeek 大语言模型生成答案
 */
@Service
public class RagService {

    private static final Logger logger = LoggerFactory.getLogger(RagService.class);

    @Autowired
    private VectorSearchService vectorSearchService;

    @Autowired
    private ChatModel chatModel;

    @Value("${rag.top-k:3}")
    private int topK;

    @Value("${rag.model:deepseek-chat}")
    private String model;

    @PostConstruct
    public void init() {
        logger.info("RAG 服务初始化完成，model: {}, topK: {}", model, topK);
    }

    /**
     * 流式处理用户问题（不带历史消息）
     */
    public void queryStream(String question, StreamCallback callback) {
        queryStream(question, new java.util.ArrayList<>(), callback);
    }

    /**
     * 流式处理用户问题（带历史消息）
     */
    public void queryStream(String question, List<Map<String, String>> history, StreamCallback callback) {
        try {
            logger.info("收到 RAG 流式查询: {}", question);

            // 1. 从向量数据库检索相关文档
            List<VectorSearchService.SearchResult> searchResults =
                    vectorSearchService.searchSimilarDocuments(question, topK);

            callback.onSearchResults(searchResults);

            if (searchResults.isEmpty()) {
                logger.warn("未找到相关文档");
                callback.onComplete("抱歉，我在知识库中没有找到相关信息来回答您的问题。", "");
                return;
            }

            // 2. 构建上下文和提示词
            String context = buildContext(searchResults);
            String systemPrompt = buildSystemPrompt(context);

            // 3. 调用 DeepSeek 模型（流式）
            String fullContent = buildRagPrompt(question, history, systemPrompt);
            callback.onComplete(fullContent, "");

        } catch (Exception e) {
            logger.error("RAG 流式查询失败", e);
            callback.onError(e);
        }
    }

    private String buildContext(List<VectorSearchService.SearchResult> searchResults) {
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < searchResults.size(); i++) {
            context.append("【参考资料 ").append(i + 1).append("】\n");
            context.append(searchResults.get(i).getContent()).append("\n\n");
        }
        return context.toString();
    }

    private String buildSystemPrompt(String context) {
        return "你是一个专业的AI助手。请根据以下参考资料回答用户的问题。\n\n"
                + "参考资料：\n" + context + "\n"
                + "请基于上述参考资料给出准确、详细的回答。如果参考资料中没有相关信息，请明确说明。";
    }

    /**
     * 构建 RAG 提示并调用模型
     */
    private String buildRagPrompt(String question, List<Map<String, String>> history, String systemPrompt) {
        // 构建消息历史
        StringBuilder fullPrompt = new StringBuilder();
        fullPrompt.append(systemPrompt).append("\n\n");

        if (!history.isEmpty()) {
            fullPrompt.append("--- 对话历史 ---\n");
            for (Map<String, String> msg : history) {
                String role = msg.get("role");
                String content = msg.get("content");
                if ("user".equals(role)) {
                    fullPrompt.append("用户: ").append(content).append("\n");
                } else if ("assistant".equals(role)) {
                    fullPrompt.append("助手: ").append(content).append("\n");
                }
            }
            fullPrompt.append("--- 对话历史结束 ---\n\n");
        }

        fullPrompt.append("用户问题：").append(question);

        // 调用 DeepSeek 模型
        Prompt prompt = new Prompt(fullPrompt.toString());
        var response = chatModel.call(prompt);
        String result = response.getResult().getOutput().getText();

        logger.info("RAG 模型调用完成，回答长度: {}", result.length());
        return result;
    }

    /**
     * 流式回调接口
     */
    public interface StreamCallback {
        void onSearchResults(List<VectorSearchService.SearchResult> results);
        void onReasoningChunk(String chunk);
        void onContentChunk(String chunk);
        void onComplete(String fullContent, String fullReasoning);
        void onError(Exception e);
    }
}
