package com.ishaan.dboptimizer.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeIngestionService {

    private static final String DOCS_PATH = "knowledge-base/docs";

    private final VectorStore vectorStore;

    public KnowledgeIngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public List<String> listDocFiles() throws IOException {
        try (Stream<Path> paths = Files.list(Paths.get(DOCS_PATH))) {
            return paths
                    .filter(p -> p.toString().endsWith(".md"))
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .collect(Collectors.toList());
        }
    }

    public String readDocContent(String filename) throws IOException {
        Path filePath = Paths.get(DOCS_PATH, filename);
        return Files.readString(filePath);
    }

    public int ingestAllDocs() throws IOException {
        List<String> files = listDocFiles();

        for (String filename : files) {
            String content = readDocContent(filename);

            Document document = new Document(
                    content,
                    Map.of("filename", filename, "source", "knowledge-base")
            );

            vectorStore.add(List.of(document));
        }

        return files.size();
    }
}