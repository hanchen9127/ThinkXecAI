package com.thinkxecai.service;

import com.thinkxecai.mapper.MapperTestSupport;

/**
 * Base class for facade service tests against the real PostgreSQL database. It reuses
 * {@link MapperTestSupport}: same Spring context, one rolled-back transaction per test, and the same
 * plain-SQL helpers for setting up related rows.
 */
public abstract class ServiceTestSupport extends MapperTestSupport {
}
