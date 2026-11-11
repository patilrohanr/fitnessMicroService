package com.fitness.aiservice.service;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitness.aiservice.model.Activity;
import com.fitness.aiservice.model.Recommendation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ActivityAIService {
    private final GeminiService geminiService;

    public Recommendation generateRecommendation(Activity activity){
        String prompt= createPromptForActivity(activity);
        String response=geminiService.getRecommendations(prompt);
        log.info("RESPONSE FROM AI {} ",response);
        return processAIResponse(activity,response);
    }

    private Recommendation processAIResponse(Activity activity, String response) {
        try{
            ObjectMapper mapper=new ObjectMapper();
            JsonNode rootNode =mapper.readTree(response);
           // JsonNode textNode=rootNode.path("candidates").get(0).path("content").get("parts").get(0).path("text");
            JsonNode textNode = rootNode.path("candidates").path(0).path("content").path("parts").path(0).path("text");

            if (textNode.isMissingNode() || textNode.asText().isBlank()) {
                log.error("No text in Gemini response: {}", response);
                return createDefaultRecommendation(activity);
            }

//            String jsonContent=textNode.asText().replace("```json\\n","").replace("n```","").trim();
            String jsonContent = textNode.asText()
                    .replaceAll("(?s)^\\s*```(?:json)?\\s*", "")
                    .replaceAll("(?s)\\s*```\\s*$", "")
                    .trim();
            log.info("RESPONSE FROM CLEANED AI {}",jsonContent);

            JsonNode analysisJson =mapper.readTree(jsonContent);
            JsonNode analysisNode = analysisJson.path("analysis");
            StringBuilder fullAnalysis= new StringBuilder();
            addAnalysisSection(fullAnalysis,analysisNode,"overall","Overall:");
            addAnalysisSection(fullAnalysis,analysisNode,"pace","Pace:");
            addAnalysisSection(fullAnalysis,analysisNode,"heartRate","Heart Rate:");
            addAnalysisSection(fullAnalysis,analysisNode,"caloriesBurned","Calories :");

            List<String> improvments = extractImprovements(analysisJson.path("improvements"));
            List<String> suggestions=extractSuggestions(analysisJson.path("suggestions"));
            List<String> safety=extractSafetyGuidelines(analysisJson.path("safety"));

        return Recommendation
                .builder()
                .activityId(activity.getId())
                .userId(activity.getUserId())
                .type(activity.getType().toString())
                .recommendation(fullAnalysis.toString().trim())
                .improvements(improvments)
                .suggestions(suggestions)
                .safety(safety)
                .createAt(LocalDateTime.now())
                .build();

        }catch(Exception e){
            log.error("Failed to parse AI response", e);
return createDefaultRecommendation(activity);
      }

    }

    private Recommendation createDefaultRecommendation(Activity activity) {
        return Recommendation.builder()
                .activityId(activity.getId())
                .userId(activity.getUserId())
                .type(activity.getType().toString())
                .recommendation("Unable to generate detailed analysis")
                .improvements(Collections.singletonList("Continue with your current routine"))
                .suggestions(Collections.singletonList("Consider consulting a fitness Consultant"))
                .safety(Arrays.asList(
                        "Always warm up before exercise",
                        "stay hydrated",
                        "Listen to your body"

                ))
                .createAt(LocalDateTime.now())
                .build();
    }

    private List<String> extractSafetyGuidelines(JsonNode safetyNode) {
                List<String> safety=new ArrayList<>();
                if(safetyNode.isArray()){
                    safetyNode.forEach(item->safety.add(item.asText()));
                }

        return safety.isEmpty()?Collections.singletonList("follow general safety guidelines"):safety;
    }

    private List<String> extractSuggestions(JsonNode suggestionsNode) {
        List<String> suggestions=new ArrayList<>();
        if(suggestionsNode.isArray()){
            suggestionsNode.forEach(suggestion->{
                String workout=suggestion.path("workout").asText();
                String description=suggestion.path("description").asText();
                suggestions.add(String.format("%s: %s",workout,description));
            });
        }
        return suggestions.isEmpty() ?
                Collections.singletonList("No specific suggestions provided"):suggestions;
    }

    private List<String> extractImprovements(JsonNode improvementsNode) {
        List<String > imporvements=new ArrayList<>();
        if(improvementsNode.isArray()){

              improvementsNode.forEach(imporvement->{
                  String area=imporvement.path("area").asText();
                  String details =imporvement.path("recommendation").asText();
                  imporvements.add(String.format("%s: %s",area,details));

              });


        }

return imporvements.isEmpty() ? Collections.singletonList("No specific improvements provided"):imporvements;
    }

    private void addAnalysisSection(StringBuilder fullAnalysis, JsonNode analysisNode, String key, String prefix) {
            if(!analysisNode.path(key).isMissingNode()){
                fullAnalysis.append(prefix).append(analysisNode.path(key).asText())
                        .append("\n\n");
            }

    }

    private String createPromptForActivity(Activity activity) {
        return String.format("""
        Analyze this fitness activity and provide detailed recommendations in the following EXACT JSON format:
        {
          "analysis": {
            "overall": "Overall analysis here",
            "pace": "Pace analysis here",
            "heartRate": "Heart rate analysis here",
            "caloriesBurned": "Calories analysis here"
          },
          "improvements": [
            {
              "area": "Area name",
              "recommendation": "Detailed recommendation"
            }
          ],
          "suggestions": [
            {
              "workout": "Workout name",
              "description": "Detailed workout description"
            }
          ],
          "safety": [
            "Safety point 1",
            "Safety point 2"
          ]
        }

        Analyze this activity:
        Activity Type: %s
        Duration: %d minutes
        Calories Burned: %d
        Additional Metrics: %s
        
        Provide detailed analysis focusing on performance, improvements, next workout suggestions, and safety guidelines.
        Ensure the response follows the EXACT JSON format shown above.
        """,
                activity.getType(),
                activity.getDuration(),
                activity.getCaloriesBurned(),
                activity.getAdditionalMetrics()
        );
    }
}
