package com.ivanfranchin.capabilitiesui.service;

import java.io.IOException;
import java.nio.file.Path;

public interface CapabilitiesArchiveClient {

    void downloadCapabilities(SupportedRepository repository, Path checkout) throws IOException;
}
