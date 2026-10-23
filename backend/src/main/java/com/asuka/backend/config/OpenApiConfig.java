package com.asuka.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger 配置。
 *
 * <p>访问 /swagger-ui.html 或 /v3/api-docs 查看接口文档。
 * 需要登录的接口通过 @SecurityRequirement(name = "sa-token") 标注。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI aiPrReviewOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI PR Review API")
                        .version("1.0.0")
                        .description("AI PR Review 平台后端接口文档"))
                .components(new Components().addSecuritySchemes("sa-token",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("Sa-Token")
                                .in(SecurityScheme.In.HEADER)
                                .name("Authorization")));
    }
}
