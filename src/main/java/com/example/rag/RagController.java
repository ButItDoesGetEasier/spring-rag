package com.example.rag;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import static org.springframework.ai.vectorstore.SearchRequest.query;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RagController {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public RagController(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
    }

    @GetMapping("/rag")
    public String generateAnswer(@RequestParam String query) {
        SearchRequest req = query(query).withSimilarityThreshold(0.7).withTopK(5);
        List<Document> similarDocuments = vectorStore.similaritySearch(req);
        String information = similarDocuments.stream()
                .map(Document::getContent)
                .collect(Collectors.joining(System.lineSeparator()));

        AtomicInteger counter = new AtomicInteger(1);
        String source = similarDocuments.stream()
                .map(doc -> doc.getMetadata().get("resourceName"))
                .filter(Objects::nonNull) // to handle any null values that might be present
                .map(resourceName -> counter.getAndIncrement() + ". " + resourceName + "<br/>")
                .collect(Collectors.joining(System.lineSeparator()));

        String test = similarDocuments.stream().toString();
        System.out.println("HERE: " + test);

        var systemPromptTemplate = new SystemPromptTemplate(
                """
                            You are a helpful assistant.
                            Use only the following book snippets to answer the question.
                            Do not use any other information. If you do not know, simply answer: Unknown.

                            {information}

                        """);
        var systemMessage = systemPromptTemplate.createMessage(Map.of("information", information));
        var userPromptTemplate = new PromptTemplate("{query}");
        var userMessage = userPromptTemplate.createMessage(Map.of("query", query));
        var prompt = new Prompt(List.of(systemMessage, userMessage));
        System.out.println(prompt);
		chatClient.prompt(prompt);
		ChatClient.ChatClientRequest.CallPromptResponseSpec responseSpec = chatClient.prompt(prompt).call();
		String content = responseSpec.chatResponse().getResult().getOutput().getContent();

        return content + "<br/><br/>Sources:<br/>" + source;
    }
}
