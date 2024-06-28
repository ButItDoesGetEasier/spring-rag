package com.example.rag;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import jakarta.annotation.PostConstruct;

@Component
public class FileLoader {
    private final SimpleVectorStore vectorStore;
    public static final String DB_URI = "C:\\Users\\beamr\\Desktop\\rag\\src\\main\\resources\\db.json";
    public static final String SCANNED_URI = "C:\\Users\\beamr\\Desktop\\rag\\src\\main\\resources\\scanned_docs.txt";
    public static final String DATA_URI = "C:\\Users\\beamr\\Desktop\\rag\\src\\main\\resources\\data";

    public FileLoader(VectorStore vectorStore) {
        this.vectorStore = (SimpleVectorStore)vectorStore;
    }

    @PostConstruct
    public void init() {
        List<String> scanned_docs = new ArrayList<>();
        File scanned_metadata = new File(SCANNED_URI);
        
        if (scanned_metadata.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(SCANNED_URI))) {
                String line;
                while ((line = br.readLine()) != null) {
                    scanned_docs.add(line);
                }
            } catch (IOException e) {
            }
        }
        
        File db = new File(DB_URI);
        if (db.exists()) {
            vectorStore.load(db);
        } 

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(DATA_URI))) {
                for (Path file : stream) {
                    if (!scanned_docs.contains(file.getFileName().toString())) {
                        Parser parser = new AutoDetectParser();
                        BodyContentHandler handler = new BodyContentHandler();
                        Metadata metadata = new Metadata();
                        FileInputStream inputstream = new FileInputStream(new File(DATA_URI+"\\"+file.getFileName().toString()));
                        ParseContext context = new ParseContext();
                        
                        try {
                            parser.parse(inputstream, handler, metadata, context);
                        } catch (SAXException | TikaException ex) {
                        }
                        // Getting the content
                        System.out.println("File content:\n" + handler.toString());
                        var textSplitter = new TokenTextSplitter(
                            400,
                            50,
                            50,
                            400,
                            true
                        );
                        List<Document> tmp = new ArrayList<>();
                        Map<String, Object> md = new HashMap<>()
                        {
                            {
                                put("resourceName", file.getFileName().toString());
                            }
                        };
                        tmp.add(new Document(handler.toString(), md));
                        vectorStore.accept(textSplitter.apply(tmp));

                        try (BufferedWriter writer = new BufferedWriter(new FileWriter(SCANNED_URI, true))) {
                            writer.write(file.getFileName().toString()+"\n");
                        } catch (IOException e) {
                        }
                    }
                }
            } catch (IOException e) {
        }
        vectorStore.save(db);
    }
}