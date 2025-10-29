package com.example.semanticfilms.service;

import com.example.semanticfilms.entity.SavedQuery;
import com.example.semanticfilms.entity.repo.SavedQueryRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.jena.query.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@RequiredArgsConstructor
public class WikiDataService {

  private final SavedQueryRepository savedQueryRepository;

  @Value("${wikidata.sparql.endpoint}")
  private String endpoint;

  /**
   * Отримує список сучасних українських фільмів (після 2010 року)
   * із назвами, режисерами, датами виходу та зображеннями.
   *
   * @param limit кількість записів для повернення
   * @return список фільмів як Map<String, String>
   */
  public List<Map<String, String>> getRecentUkrainianFilms(Set<String> genres, Instant startDate,
                                                           Instant endDate, int limit) {

    ServletRequestAttributes servletRequestAttributes =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    HttpServletRequest request = servletRequestAttributes.getRequest();

    final var queryUrl = request.getRequestURI().concat("?").concat(request.getQueryString());
    final var savedQuery = savedQueryRepository.findByQueryUrl(queryUrl);
    if (savedQuery.isPresent()) {
      System.out.println("FOUND SAVED QUERY");
      return savedQuery.get().getResult();
    }

    String dateFilter = "";

    if (startDate != null && endDate != null && (startDate.isBefore(endDate))) {
      dateFilter =
          String.format("FILTER(?date >= \"%s\"^^xsd:dateTime && ?date <= \"%s\"^^xsd:dateTime)",
              startDate, endDate);
    }

    String genresList = "";

    if (genres != null) {
      if (!genres.isEmpty()) {
        genresList = "VALUES ?genre {%s}".formatted(
            genres.stream().map(genre -> "wd:".concat(genre)).collect(Collectors.joining(" ")));
      }
    }

    // SPARQL-запит до відкритого endpoint Wikidata
    String sparql =
        """
            PREFIX wd: <http://www.wikidata.org/entity/>
            PREFIX wdt: <http://www.wikidata.org/prop/direct/>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX wikibase: <http://wikiba.se/ontology#>
            PREFIX bd: <http://www.bigdata.com/rdf#>
            PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                    
            SELECT DISTINCT ?film ?filmLabel ?directorLabel (MAX(?date) AS ?latestDate)
            (GROUP_CONCAT(DISTINCT ?gLabel; separator=", ") AS ?genres)
                WHERE {
                   ?film wdt:P31 wd:Q11424;
                   wdt:P495 wd:Q212;
                   wdt:P136 ?genre.
                      %s
                   OPTIONAL { ?film wdt:P57 ?director. }
                   OPTIONAL { ?film wdt:P577 ?date. }
                   OPTIONAL {?film wdt:P136 ?genre.
                      OPTIONAL { ?genre rdfs:label ?gLabel.
                          FILTER (lang(?gLabel) = "uk" || lang(?gLabel) = "")}}
                   %s
                   SERVICE wikibase:label { bd:serviceParam wikibase:language "uk". }
                   }
                   GROUP BY ?film ?filmLabel ?directorLabel ?latestDate
                   ORDER BY DESC(?latestDate)
                   LIMIT %d """.formatted(genresList, dateFilter, limit);

    // Створюємо об'єкт запиту
    Query query = QueryFactory.create(sparql);

    // Результати
    List<Map<String, String>> results = new ArrayList<>();

    // Виконуємо запит через стандартний HTTP SPARQL endpoint
    try (QueryExecution qexec = QueryExecutionFactory.sparqlService(endpoint, query)) {

      // Отримуємо результати
      ResultSet resultSet = qexec.execSelect();

      while (resultSet.hasNext()) {
        QuerySolution sol = resultSet.nextSolution();
        Map<String, String> film = new LinkedHashMap<>();

        film.put("film", sol.contains("film") ? sol.getResource("film").getURI() : "");
        film.put("title", sol.contains("filmLabel") ? sol.getLiteral("filmLabel").getString() : "");
        film.put("director",
            sol.contains("directorLabel") ? sol.getLiteral("directorLabel").getString() : "");
        film.put("date",
            sol.contains("latestDate") ? sol.getLiteral("latestDate").getString() : "");
        film.put("genres", sol.contains("genres") ? sol.getLiteral("genres").getString() : "");

        results.add(film);
      }

    } catch (Exception e) {
      System.err.println("Помилка при виконанні SPARQL-запиту:");
      e.printStackTrace();
    }
    savedQueryRepository.save(SavedQuery.builder().queryUrl(queryUrl).result(results).build());
    System.out.println(results.size());
    return results;
  }

  public List<Map<String, String>> getGenres(int offset, int limit) {

    ServletRequestAttributes servletRequestAttributes =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    HttpServletRequest request = servletRequestAttributes.getRequest();
    final String queryUrl = request.getRequestURI() + "?" + request.getQueryString();

    // Check if query already saved
    final var savedQuery = savedQueryRepository.findByQueryUrl(queryUrl);
    if (savedQuery.isPresent()) {
      System.out.println("FOUND SAVED QUERY");
      return savedQuery.get().getResult();
    }

    // SPARQL query to fetch genres from Wikidata
    String sparql = """
        PREFIX wd: <http://www.wikidata.org/entity/>
        PREFIX wdt: <http://www.wikidata.org/prop/direct/>
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        PREFIX wikibase: <http://wikiba.se/ontology#>
        PREFIX bd: <http://www.bigdata.com/rdf#>

        SELECT DISTINCT ?genre ?genreLabel
        WHERE {
            ?genre wdt:P31 wd:Q188451;   # Q188451 = film genre
                   rdfs:label ?genreLabel.
            FILTER(LANG(?genreLabel) = "uk" || LANG(?genreLabel) = "")
        }
        ORDER BY ?genreLabel
        OFFSET %d
        LIMIT %d
        """.formatted(offset, limit);

    Query query = QueryFactory.create(sparql);
    List<Map<String, String>> results = new ArrayList<>();

    try (QueryExecution qexec = QueryExecutionFactory.sparqlService(endpoint, query)) {
      ResultSet resultSet = qexec.execSelect();
      while (resultSet.hasNext()) {
        QuerySolution sol = resultSet.nextSolution();
        Map<String, String> genre = new LinkedHashMap<>();
        genre.put("id", sol.contains("genre") ? sol.getResource("genre").getURI() : "");
        genre.put("label",
            sol.contains("genreLabel") ? sol.getLiteral("genreLabel").getString() : "");
        results.add(genre);
      }
    } catch (Exception e) {
      System.err.println("Error executing SPARQL query for genres:");
      e.printStackTrace();
    }

    // Save results for caching
    savedQueryRepository.save(SavedQuery.builder()
        .queryUrl(queryUrl)
        .result(results)
        .build());

    System.out.println("Fetched genres: " + results.size());
    return results;
  }

}

