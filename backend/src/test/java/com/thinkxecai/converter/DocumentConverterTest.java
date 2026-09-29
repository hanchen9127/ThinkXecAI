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

    private final DocumentConverter converter = new DocumentConverter(new ObjectMapper());

    @Test
    void metadataRoundTripsBetweenDtoAndEntity() {
        DocumentDTO dto = DocumentDTO.builder()
                .id(UUID.randomUUID())
                .kbId(UUID.randomUUID())
                .name("guide.md")
                .metadata(Map.of("filePath", "kb-1/doc-1/uuid.md", "sizeBytes", 1024))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        DocumentEntity entity = converter.toEntity(dto);
        assertThat(entity.getMetadata()).contains("filePath").contains("kb-1/doc-1/uuid.md");

        DocumentDTO roundTripped = converter.toDTO(entity);
        assertThat(roundTripped.getMetadata()).isEqualTo(dto.getMetadata());
    }

}
