package name.golets.order.hunter.common.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

/** Permissions metadata returned by the external records API. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Permissions {

  @JsonProperty("may_read_fields")
  private List<String> mayReadFields;

  @JsonProperty("may_update")
  private String mayUpdate;

  @JsonProperty("may_delete")
  private String mayDelete;

  @JsonProperty("may_update_fields")
  private List<String> mayUpdateFields;
}
