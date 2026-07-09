package com.bot.whatsappbotservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitingFilterTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private RateLimitProperties properties;
    private RateLimitingFilter filter;
    private FilterChain chain;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        properties = new RateLimitProperties(true,
                new RateLimitProperties.Window(3, 60),
                new RateLimitProperties.Window(5, 60),
                new RateLimitProperties.Window(2, 300));
        filter = new RateLimitingFilter(redisTemplate, properties);
        chain = mock(FilterChain.class);
    }

    @Test
    void passesThroughUnmatchedPathsWithoutTouchingRedis() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/catalog/products");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void allowsRequestsUnderTheLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(valueOperations.increment(anyString())).thenReturn(1L);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(redisTemplate).expire(anyString(), any(java.time.Duration.class));
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void rejectsRequestsOverTheLimitWithoutSettingExpiryAgain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(valueOperations.increment(anyString())).thenReturn(4L);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        verify(redisTemplate, never()).expire(anyString(), any(java.time.Duration.class));
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentAsString()).contains("RATE_LIMIT_EXCEEDED");
    }

    @Test
    void failsOpenWhenRedisIsUnreachable() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(valueOperations.increment(anyString())).thenThrow(new RuntimeException("connection refused"));

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void disabledConfigurationBypassesRateLimitingEntirely() throws Exception {
        RateLimitProperties disabled = new RateLimitProperties(false,
                new RateLimitProperties.Window(3, 60), new RateLimitProperties.Window(5, 60),
                new RateLimitProperties.Window(2, 300));
        RateLimitingFilter disabledFilter = new RateLimitingFilter(redisTemplate, disabled);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        disabledFilter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void appliesSeparateLimitToWebhookPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/whatsapp/webhook");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(valueOperations.increment(anyString())).thenReturn(6L);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    void appliesOtpLimitToStorefrontLoginPathDespiteVariableSlugSegment() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/store/acme-dairy/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(valueOperations.increment(anyString())).thenReturn(3L);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    void doesNotRateLimitOtherStorefrontPaths() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/store/acme-dairy/products");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(redisTemplate, never()).opsForValue();
    }
}
