package com.thinkxecai.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    void successWithoutDataReturnsCode200AndNullData() {
        ApiResponse<Void> response = ApiResponse.success();

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("success");
        assertThat(response.getData()).isNull();
    }

    @Test
    void successWithDataReturnsCode200AndTheData() {
        ApiResponse<String> response = ApiResponse.success("payload");

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("success");
        assertThat(response.getData()).isEqualTo("payload");
    }

    @Test
    void errorWithoutCodeDefaultsTo500() {
        ApiResponse<Void> response = ApiResponse.error("boom");

        assertThat(response.getCode()).isEqualTo(500);
        assertThat(response.getMessage()).isEqualTo("boom");
        assertThat(response.getData()).isNull();
    }

    @Test
    void errorWithExplicitCodeUsesThatCode() {
        ApiResponse<Void> response = ApiResponse.error(404, "not found");

        assertThat(response.getCode()).isEqualTo(404);
        assertThat(response.getMessage()).isEqualTo("not found");
        assertThat(response.getData()).isNull();
    }

}
