package com.ivanfranchin.capabilitiesui.application;

import com.ivanfranchin.capabilitiesui.service.CapabilityTreeService;
import com.ivanfranchin.capabilitiesui.service.CapabilityTreeService.CapabilityTreeResponse;
import org.springframework.stereotype.Service;

@Service
public class ViewCapabilitiesManagerUseCase {

    private final CapabilityTreeService capabilityTreeService;

    public ViewCapabilitiesManagerUseCase(CapabilityTreeService capabilityTreeService) {
        this.capabilityTreeService = capabilityTreeService;
    }

    public CapabilityTreeResponse viewCapabilitiesTree() {
        return capabilityTreeService.tree();
    }
}
