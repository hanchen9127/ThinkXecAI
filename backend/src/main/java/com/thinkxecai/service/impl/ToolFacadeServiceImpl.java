package com.thinkxecai.service.impl;

import com.thinkxecai.agent.tool.Tool;
import com.thinkxecai.agent.tool.ToolType;
import com.thinkxecai.common.LogText;
import com.thinkxecai.model.vo.ToolVO;
import com.thinkxecai.service.ToolFacadeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ToolFacadeServiceImpl implements ToolFacadeService {

    private final List<Tool> tools;

    /** All {@link Tool} beans. Names are what agents store, so two tools with one name stop startup. */
    public ToolFacadeServiceImpl(List<Tool> tools) {
        List<String> duplicates = tools.stream()
                .collect(Collectors.groupingBy(Tool::getName, Collectors.counting()))
                .entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        if (!duplicates.isEmpty()) {
            throw new IllegalStateException("Duplicate tool names: " + String.join(", ", duplicates));
        }
        this.tools = tools.stream()
                .sorted(Comparator.comparing(Tool::getType).thenComparing(Tool::getName))
                .toList();
    }

    @Override
    public List<ToolVO> listTools() {
        return tools.stream().map(ToolFacadeServiceImpl::toVO).toList();
    }

    @Override
    public List<ToolVO> listFixedTools() {
        return ofType(ToolType.FIXED);
    }

    @Override
    public List<ToolVO> listOptionalTools() {
        return ofType(ToolType.OPTIONAL);
    }

    /**
     * Built per call from the tool beans; {@link MethodToolCallbackProvider} reads each bean's
     * {@code @Tool} method and refuses two callbacks with one model-facing name.
     */
    @Override
    public List<ToolCallback> toolCallbacksFor(List<String> allowedTools) {
        Set<String> requested = allowedTools == null ? Set.of() : allowedTools.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<String> optional = tools.stream()
                .filter(tool -> tool.getType() == ToolType.OPTIONAL)
                .map(Tool::getName)
                .collect(Collectors.toSet());
        List<String> skipped = requested.stream()
                .filter(name -> !optional.contains(name))
                .sorted()
                .toList();
        if (!skipped.isEmpty()) {
            log.warn("Skipping allowedTools entries that are not optional tools: {}", LogText.safe(skipped));
        }
        Object[] selected = tools.stream()
                .filter(tool -> tool.getType() == ToolType.FIXED || requested.contains(tool.getName()))
                .toArray();
        return List.of(MethodToolCallbackProvider.builder().toolObjects(selected).build().getToolCallbacks());
    }

    private List<ToolVO> ofType(ToolType type) {
        return tools.stream()
                .filter(tool -> tool.getType() == type)
                .map(ToolFacadeServiceImpl::toVO)
                .toList();
    }

    private static ToolVO toVO(Tool tool) {
        return new ToolVO(tool.getName(), tool.getDescription(), tool.getType());
    }

}
