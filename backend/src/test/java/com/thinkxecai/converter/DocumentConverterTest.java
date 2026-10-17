package com.thinkxecai.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkxecai.model.dto.DocumentDTO;
import com.thinkxecai.model.entity.DocumentEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentConverterTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final DocumentConverter converter = new DocumentConverter(JSON);

    @Test
    void metadataRoundTripsBetweenDtoAndEntity() throws Exception {
        DocumentDTO dto = DocumentDTO.builder()
                .id(UUID.randomUUID())
                .kbId(UUID.randomUUID())
                .name("guide.md")
                .metadata(Map.of("filePath", "kb-1/doc-1/uuid.md", "sizeBytes", 1024))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        DocumentEntity entity = converter.toEntity(dto);
        assertThat(JSON.readTree(entity.getMetadata()))
                .isEqualTo(JSON.readTree("{\"filePath\": \"kb-1/doc-1/uuid.md\", \"sizeBytes\": 1024}"));

        DocumentDTO roundTripped = converter.toDTO(entity);
        assertThat(roundTripped.getMetadata()).isEqualTo(dto.getMetadata());
    }

}
