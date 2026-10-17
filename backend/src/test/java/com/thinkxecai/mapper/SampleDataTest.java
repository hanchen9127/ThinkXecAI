package com.thinkxecai.mapper;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The seeded e-commerce sample tables the SQL tool can query (Phase 2): exactly these 15, each with rows. */
class SampleDataTest extends MapperTestSupport {

    private static final List<String> SAMPLE_TABLES = List.of(
            "t_app_user", "t_role", "t_user_role", "t_product_category", "t_product", "t_product_category_relation",
            "t_order_header", "t_order_item", "t_payment", "t_shipment", "t_comment_topic", "t_comment",
            "t_comment_topic_mapping", "t_comment_summary_daily", "t_system_kv");

    @Test
    void exactlyTheSpecifiedSampleTablesExist() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_name LIKE 't\\_%'",
                String.class);

        assertThat(tables).containsExactlyInAnyOrderElementsOf(SAMPLE_TABLES);
    }

    @Test
    void everySampleTableHasRows() {
        assertThat(SAMPLE_TABLES).allSatisfy(table ->
                assertThat(countRows(table, "true")).as(table).isPositive());
    }

}
