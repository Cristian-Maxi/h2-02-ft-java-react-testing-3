package com.app.waki.match.application.servicesImpl;

import com.app.waki.match.domain.standings.LeagueStanding;
import com.app.waki.match.domain.standings.Standing;
import com.app.waki.match.domain.standings.StandingRepository;
import com.app.waki.match.domain.standings.TeamStatistics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StandingServiceImpl implements StandingService {

    private static final String API_URL = "https://v3.football.api-sports.io/standings?league=39&season=2024";


    private final StandingRepository standingRepository;

    @Value("${API_TOKEN}")
    private String apiToken;

    @Override
    public void fetchAndSaveStandings() throws IOException, InterruptedException {

        // Crea la solicitud HTTP
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("x-rapidapi-key", apiToken)
                .method("GET", HttpRequest.BodyPublishers.noBody())
                .build();

        // Envía la solicitud y recibe la respuesta
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        log.info("API Response: {}", response.body());

        // Procesa el JSON
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = objectMapper.readTree(response.body());

        // Nodo que contiene los standings
        JsonNode standingsNode = rootNode.path("response");

        // Inicializa la lista para almacenar los standings
        List<Standing> standingsList = new ArrayList<>();

        // Itera sobre cada elemento del array 'response'
        standingsNode.forEach(standingGroupNode -> {
            standingGroupNode.path("league").path("standings").forEach(standingNode -> {
                standingNode.forEach(teamStandingNode -> {
                    Standing standing = new Standing();
                    standing.setPosition(teamStandingNode.path("rank").asInt());
                    standing.setPoints(teamStandingNode.path("points").asInt());
                    standing.setGoalsDiff(teamStandingNode.path("goalsDiff").asInt());
                    standing.setTeamId(teamStandingNode.path("team").path("id").asInt());
                    standing.setTeamName(teamStandingNode.path("team").path("name").asText());
                    standing.setTeamLogo(teamStandingNode.path("team").path("logo").asText());

                    // Establece los detalles de la liga
                    LeagueStanding league = new LeagueStanding();
                    league.setLeagueId(standingGroupNode.path("league").path("id").asLong());
                    league.setLeagueName(standingGroupNode.path("league").path("name").asText());
                    league.setCountry(standingGroupNode.path("league").path("country").asText());
                    league.setLeagueLogo(standingGroupNode.path("league").path("logo").asText());
                    league.setSeason(standingGroupNode.path("league").path("season").asInt());
                    standing.setLeague(league);

                    // Establece las estadísticas del equipo
                    TeamStatistics teamStats = new TeamStatistics();
                    JsonNode allStats = teamStandingNode.path("all");
                    teamStats.setPlayed(allStats.path("played").asInt());
                    teamStats.setWin(allStats.path("win").asInt());
                    teamStats.setDraw(allStats.path("draw").asInt());
                    teamStats.setLose(allStats.path("lose").asInt());
                    standing.setAll(teamStats);  // Asegúrate de tener 'setAll' en 'Standing'

                    // Establece la fecha de última actualización
                    String update = teamStandingNode.path("update").asText();
                    if (!update.isEmpty()) { // Verifica que no esté vacío antes de parsear
                        standing.setLastUpdate(OffsetDateTime.parse(update));
                    }

                    // Agrega el objeto Standing a la lista
                    standingsList.add(standing);
                });
            });
        });

        // Guarda todos los standings en la base de datos
        standingRepository.saveAll(standingsList);
        log.info("Standings saved successfully.");
    }

    @Override
    @Transactional
    public List<Standing> getStandingsByLeagueId(Long leagueId) {
        return standingRepository.findByLeague_leagueId(leagueId);
    }
}