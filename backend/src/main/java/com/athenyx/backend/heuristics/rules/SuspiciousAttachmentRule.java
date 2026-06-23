package com.athenyx.backend.heuristics.rules;

import com.athenyx.backend.heuristics.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class SuspiciousAttachmentRule implements HeuristicRule {

    private static final List<String> DANGEROUS_EXTENSIONS = List.of(
        ".exe", ".bat", ".scr", ".cmd", ".vbs", ".js", ".jar", ".iso",
        ".msi", ".pif", ".application", ".gadget", ".hta", ".cpl",
        ".msc", ".ws", ".wsf", ".ps1", ".ps2", ".msh", ".reg"
    );

    private static final List<String> ARCHIVE_EXTENSIONS = List.of(
        ".zip", ".rar", ".7z", ".tar", ".gz", ".bz2"
    );

    private static final Pattern ZIP_INSIDE_PATTERN = Pattern.compile(
        "(?i)(\\.zip/.+\\.(exe|bat|scr|cmd|vbs|js|jar|msi|pif)|" +
        "\\.(exe|bat|scr|cmd|vbs|js|jar|msi|pif).*\\.zip)"
    );

    @Override
    public String name() {
        return "SuspiciousAttachmentRule";
    }

    @Override
    public RuleSeverity severity() {
        return RuleSeverity.HIGH;
    }

    @Override
    public Optional<HeuristicFinding> apply(EmailHeuristicsInput input) {
        boolean hasDangerous = false;
        boolean hasArchive = false;
        boolean hasArchiveWithExecutable = false;

        for (String url : input.urls()) {
            if (url == null) continue;
            String lower = url.toLowerCase();

            for (String ext : DANGEROUS_EXTENSIONS) {
                if (lower.contains(ext)) {
                    hasDangerous = true;
                }
            }

            for (String ext : ARCHIVE_EXTENSIONS) {
                if (lower.contains(ext)) {
                    hasArchive = true;
                }
            }

            if (ZIP_INSIDE_PATTERN.matcher(lower).find()) {
                hasArchiveWithExecutable = true;
            }
        }

        if (!hasDangerous && !hasArchive && !hasArchiveWithExecutable) {
            return Optional.empty();
        }

        int score;
        String description;
        if (hasArchiveWithExecutable) {
            score = 95;
            description = "Archivo comprimido que contiene ejecutable detectado";
        } else if (hasDangerous) {
            score = 60;
            description = "Extensión de archivo peligrosa detectada: " +
                input.urls().stream()
                    .filter(u -> u != null)
                    .filter(u -> DANGEROUS_EXTENSIONS.stream().anyMatch(e -> u.toLowerCase().contains(e)))
                    .findFirst()
                    .map(u -> u.substring(u.lastIndexOf('.') + 1).toUpperCase())
                    .orElse("desconocida");
        } else {
            score = 40;
            description = "Archivo comprimido detectado en los enlaces del correo";
        }

        return Optional.of(new HeuristicFinding(name(), description, score));
    }
}
