package com.hxh.apboa.assessment.service.impl;

import com.hxh.apboa.assessment.mapper.ExamCampaignMapper;
import com.hxh.apboa.assessment.mapper.ExamEnrollmentMapper;
import com.hxh.apboa.common.dto.ExamCandidateModelConfigRequest;
import com.hxh.apboa.common.entity.ExamCampaign;
import com.hxh.apboa.common.entity.ExamEnrollment;
import com.hxh.apboa.common.entity.ModelConfig;
import com.hxh.apboa.common.entity.ModelProvider;
import com.hxh.apboa.common.vo.ExamCandidateModelConfigVO;
import com.hxh.apboa.model.mapper.ModelConfigMapper;
import com.hxh.apboa.model.mapper.ModelProviderMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamCandidateModelConfigServiceImplTest {
    @Mock
    private ExamCampaignMapper campaignMapper;
    @Mock
    private ExamEnrollmentMapper enrollmentMapper;
    @Mock
    private ModelProviderMapper providerMapper;
    @Mock
    private ModelConfigMapper configMapper;

    private ExamCandidateModelConfigServiceImpl service;
    private HttpServer modelServer;

    @BeforeEach
    void setUp() {
        service = new ExamCandidateModelConfigServiceImpl(
                campaignMapper, enrollmentMapper, providerMapper, configMapper);

        ExamCampaign campaign = new ExamCampaign();
        campaign.setSubmissionStart(LocalDateTime.now().plusHours(1));
        when(campaignMapper.selectById(1L)).thenReturn(campaign);
        when(enrollmentMapper.selectOne(any())).thenReturn(new ExamEnrollment());
    }

    @AfterEach
    void tearDown() {
        if (modelServer != null) {
            modelServer.stop(0);
        }
    }

    @Test
    void rejectsMalformedCustomBaseUrlBeforePersisting() {
        ExamCandidateModelConfigRequest request = request("https:// {invalid} .example.com/v1", "model-a");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.save(1L, 2L, request));

        assertEquals("Base URL 格式错误，请填写完整的 HTTP(S) 地址", error.getMessage());
        verify(providerMapper, never()).insert(any(ModelProvider.class));
        verify(configMapper, never()).insert(any(ModelConfig.class));
    }

    @Test
    void refusesModelIdThatProviderDoesNotExpose() throws IOException {
        String baseUrl = startModelServer("valid-model");
        ExamCandidateModelConfigRequest request = request(baseUrl, "unknown-model");

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.save(1L, 2L, request));

        assertEquals("模型 ID 不在该 API 返回的可用模型列表中，请重新选择", error.getMessage());
        verify(providerMapper, never()).insert(any(ModelProvider.class));
        verify(configMapper, never()).insert(any(ModelConfig.class));
    }

    @Test
    void persistsOnlyAfterProviderAndModelAreVerified() throws IOException {
        String baseUrl = startModelServer("valid-model");
        when(providerMapper.insert(any(ModelProvider.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, ModelProvider.class).setId(10L);
            return 1;
        });
        when(configMapper.insert(any(ModelConfig.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, ModelConfig.class).setId(20L);
            return 1;
        });

        ExamCandidateModelConfigVO result = service.save(1L, 2L, request(baseUrl, "valid-model"));

        assertEquals("CONNECTED", result.getConnectivityStatus());
        assertEquals("保存时连接检测通过", result.getConnectivityMessage());
        verify(providerMapper).insert(any(ModelProvider.class));
        verify(configMapper).insert(any(ModelConfig.class));
        verify(enrollmentMapper).updateById(any(ExamEnrollment.class));
    }

    private ExamCandidateModelConfigRequest request(String baseUrl, String modelId) {
        ExamCandidateModelConfigRequest request = new ExamCandidateModelConfigRequest();
        request.setProviderKey("custom");
        request.setModelId(modelId);
        request.setApiKey("test-key");
        request.setBaseUrl(baseUrl);
        return request;
    }

    private String startModelServer(String modelId) throws IOException {
        modelServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        modelServer.createContext("/models", exchange -> {
            byte[] body = ("{\"data\":[{\"id\":\"" + modelId + "\"}]}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        modelServer.start();
        return "http://127.0.0.1:" + modelServer.getAddress().getPort();
    }
}
