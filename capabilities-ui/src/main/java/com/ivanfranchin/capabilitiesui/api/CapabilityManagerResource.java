package com.ivanfranchin.capabilitiesui.api;

import com.ivanfranchin.capabilitiesui.application.ViewCapabilitiesManagerUseCase;
import com.ivanfranchin.capabilitiesui.service.CapabilityTreeService.CapabilityTreeResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/capabilities", produces = MediaType.APPLICATION_JSON_VALUE)
public class CapabilityManagerResource {

    private final ViewCapabilitiesManagerUseCase viewCapabilitiesManager;

    public CapabilityManagerResource(ViewCapabilitiesManagerUseCase viewCapabilitiesManager) {
        this.viewCapabilitiesManager = viewCapabilitiesManager;
    }

    @GetMapping("/tree")
    public CapabilityTreeResponse tree() {
        return viewCapabilitiesManager.viewCapabilitiesTree();
    }
}
