package name.golets.order.hunter.common.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;

/** Wrapper for paged order API response payload. */
@Data
@Accessors(chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrdersResponse {

  @JsonProperty("result_info")
  private ResultInfo resultInfo;

  private List<Record> records = new ArrayList<>();
}
