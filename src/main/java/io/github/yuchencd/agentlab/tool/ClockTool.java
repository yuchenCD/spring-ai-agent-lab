package io.github.yuchencd.agentlab.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Returns the current date and time in a requested IANA time zone. */
@Component
public class ClockTool implements Tool {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Override
  public String name() {
    return "clock";
  }

  @Override
  public String description() {
    return "查询当前日期和时间；可选 timezone 参数（IANA 时区，默认 Asia/Shanghai）。";
  }

  @Override
  public Map<String, Object> parameters() {
    return Map.of(
        "type", "object",
        "properties", Map.of(
            "timezone", Map.of(
                "type", "string",
                "description", "IANA 时区，例如 Asia/Shanghai、UTC")));
  }

  @Override
  public String execute(String argumentsJson) {
    String timezone = "Asia/Shanghai";
    try {
      JsonNode arguments = JSON.readTree(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
      timezone = arguments.path("timezone").asText(timezone);
    } catch (Exception exception) {
      throw new IllegalArgumentException("arguments 不是合法 JSON：" + exception.getMessage());
    }

    ZoneId zone;
    try {
      zone = ZoneId.of(timezone);
    } catch (DateTimeException exception) {
      throw new IllegalArgumentException("未知时区：" + timezone);
    }

    ZonedDateTime now = ZonedDateTime.now(zone);
    return now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) + " " + now.getDayOfWeek();
  }
}
