package name.golets.order.hunter.common.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.experimental.Accessors;

/** Raw record entity mapped directly from external order API documents. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@Accessors(chain = true)
public class Record {

  @JsonProperty("_sid")
  private String sid;

  @JsonProperty("orders_v3_2__product_title")
  private String orderProductTitle;

  @JsonProperty("orders_v3_2__name")
  private String orderName;

  @JsonProperty("orders_v3_2__date")
  private String orderDate;

  @JsonProperty("orders_v3_2__artist")
  private String artist;

  @JsonProperty("orders_v3_2__design_status")
  private String designStatus;

  //    @JsonProperty("_permissions")
  //    private Permissions permissions;

  @JsonProperty("_primary")
  private String primary;

  @JsonProperty("_object_id")
  private String objectId;

  @JsonProperty("orders_v3_2__additional_pet")
  private String additionalPet;

  @JsonProperty("orders_v3_2__shipping_title")
  private String shippingTitle;

  @JsonProperty("orders_v3_2__source_fast_track_order")
  private String sourceFastTrackOrder;
}
