package id.ac.ui.cs.advprog.yomu.auth.internal.service;

import java.util.Map;

public interface GoogleSsoService {
    Map<String, String> verifyToken(String accessToken);
}
