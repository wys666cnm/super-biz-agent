package org.example.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import jakarta.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 向量嵌入服务
 * 使用 BGE-M3 模型（本地 Ollama 部署），输出 1024 维向量
 *
 * 调用 Ollama /api/embed 接口：
 * POST http://localhost:11434/api/embed
 * {"model": "bge-m3", "input": "text"}
 *
 * 返回格式：{"model": "bge-m3", "embeddings": [[0.1, 0.2, ...]]}
 */
@Service
public class VectorEmbeddingService {

    private static final Logger logger = LoggerFactory.getLogger(VectorEmbeddingService.class);

    @Value("${embedding.base-url}")
    private String baseUrl;

    @Value("${embedding.model}")
    private String model;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public VectorEmbeddingService() {
        this.restClient = RestClient.builder().build();
        this.objectMapper = new ObjectMapper();
    }

    @PostConstruct
    public void init() {
        logger.info("BGE-M3 Embedding 服务初始化完成，模型: {}, Ollama URL: {}", model, baseUrl);
    }

    /**
     * 生成单个文本的向量嵌入
     */
    public List<Float> generateEmbedding(String content) {
        try {
            if (content == null || content.trim().isEmpty()) {
                throw new IllegalArgumentException("内容不能为空");
            }

            logger.debug("开始生成向量嵌入, 内容长度: {} 字符", content.length());

            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "input", content
            );

            String responseJson = restClient.post()
                    .uri(baseUrl + "/api/embed")
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            Map<String, Object> response = objectMapper.readValue(responseJson,
                    new TypeReference<Map<String, Object>>() {});

            @SuppressWarnings("unchecked")
            List<List<Double>> embeddings = (List<List<Double>>) response.get("embeddings");

            if (embeddings == null || embeddings.isEmpty()) {
                throw new RuntimeException("Ollama 返回空向量");
            }

            List<Double> embeddingDoubles = embeddings.get(0);
            List<Float> floatEmbedding = new java.util.ArrayList<>(embeddingDoubles.size());
            for (Double value : embeddingDoubles) {
                floatEmbedding.add(value.floatValue());
            }

            logger.debug("成功生成向量嵌入, 维度: {}", floatEmbedding.size());
            return floatEmbedding;

        } catch (Exception e) {
            logger.error("生成向量嵌入失败", e);
            throw new RuntimeException("生成向量嵌入失败: " + e.getMessage(), e);
        }
    }

    /**
     * 批量生成向量嵌入
     */
    @SuppressWarnings("unchecked")
    public List<List<Float>> generateEmbeddings(List<String> contents) {
        try {
            if (contents == null || contents.isEmpty()) {
                return Collections.emptyList();
            }

            logger.info("开始批量生成向量嵌入, 数量: {}", contents.size());

            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "input", contents
            );

            String responseJson = restClient.post()
                    .uri(baseUrl + "/api/embed")
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            Map<String, Object> response = objectMapper.readValue(responseJson,
                    new TypeReference<Map<String, Object>>() {});

            List<List<Double>> embeddings = (List<List<Double>>) response.get("embeddings");

            if (embeddings == null || embeddings.isEmpty()) {
                throw new RuntimeException("Ollama 批量返回空向量");
            }

            List<List<Float>> result = new java.util.ArrayList<>();
            for (List<Double> embedding : embeddings) {
                List<Float> floatEmbedding = new java.util.ArrayList<>(embedding.size());
                for (Double value : embedding) {
                    floatEmbedding.add(value.floatValue());
                }
                result.add(floatEmbedding);
            }

            logger.info("成功批量生成向量嵌入, 数量: {}, 维度: {}",
                    result.size(), result.isEmpty() ? 0 : result.get(0).size());

            return result;

        } catch (Exception e) {
            logger.error("批量生成向量嵌入失败", e);
            throw new RuntimeException("批量生成向量嵌入失败: " + e.getMessage(), e);
        }
    }

    public List<Float> generateQueryVector(String query) {
        return generateEmbedding(query);
    }

    public float calculateCosineSimilarity(List<Float> vector1, List<Float> vector2) {
        if (vector1.size() != vector2.size()) {
            throw new IllegalArgumentException("向量维度不匹配: " + vector1.size() + " vs " + vector2.size());
        }
        float dotProduct = 0.0f;
        float norm1 = 0.0f;
        float norm2 = 0.0f;
        for (int i = 0; i < vector1.size(); i++) {
            dotProduct += vector1.get(i) * vector2.get(i);
            norm1 += vector1.get(i) * vector1.get(i);
            norm2 += vector2.get(i) * vector2.get(i);
        }
        return dotProduct / (float) (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
}
