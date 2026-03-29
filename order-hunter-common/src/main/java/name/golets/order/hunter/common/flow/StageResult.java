package name.golets.order.hunter.common.flow;

/**
 * Marker contract for data produced by a stage and stored in flow context.
 *
 * @param <TStage> stage type that produced this result
 */
public interface StageResult<TStage extends Stage<?>> {

  /**
   * Declares the producing stage type for this result.
   *
   * @return stage class associated with this result
   */
  Class<TStage> stageType();
}
