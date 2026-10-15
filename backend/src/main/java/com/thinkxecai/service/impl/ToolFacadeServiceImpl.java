package com.thinkxecai.service.impl;

import com.thinkxecai.agent.tool.Tool;
import com.thinkxecai.agent.tool.ToolType;
import com.thinkxecai.model.vo.ToolVO;
import com.thinkxecai.service.ToolFacadeService;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
