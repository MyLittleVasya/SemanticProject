package com.example.semanticfilms.api;

import com.example.semanticfilms.service.WikiDataService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"},allowedHeaders = "*", exposedHeaders = "*", allowCredentials = "true")
public class MainController {

  private final WikiDataService wikidataService;

  @GetMapping("/films")
  public ResponseEntity<List<Map<String, String>>> recentFilms(@RequestParam(defaultValue = "50") int limit,
                                                               @RequestParam(required = false) Instant startDate,
                                                               @RequestParam(required = false) Instant endDate,
                                                               @RequestParam(required = false)
                                                               Set<String> genres) {
    List<Map<String, String>> films = wikidataService.getRecentUkrainianFilms(genres, startDate, endDate, limit);
    return ResponseEntity.ok(films);
  }

  @GetMapping("/genres")
  public List<Map<String, String>> getGenres(
      @RequestParam(defaultValue = "0") int offset,
      @RequestParam(defaultValue = "50") int limit
  ) {
    return wikidataService.getGenres(offset, limit);
  }

}
