package name.golets.order.hunter.orderhunterworker.stage.results;

import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import name.golets.order.hunter.common.flow.StageResult;
import name.golets.order.hunter.common.model.Record;
import name.golets.order.hunter.orderhunterworker.stage.PollRecordsStage;

/** Raw poll payload and retrieval metadata produced by {@link PollRecordsStage}. */
@Getter
public class PollRecordsStageResult implements StageResult<PollRecordsStage> {

  @Getter(AccessLevel.NONE)
  private final List<Record> records = new ArrayList<>();

  @Override
  public Class<PollRecordsStage> stageType() {
    return PollRecordsStage.class;
  }

  public List<Record> getRecords() {
    return List.copyOf(records);
  }

  /** Appends a fetched record for downstream parsing. */
  public void addRecord(Record record) {
    if (record != null) {
      records.add(record);
    }
  }
}
