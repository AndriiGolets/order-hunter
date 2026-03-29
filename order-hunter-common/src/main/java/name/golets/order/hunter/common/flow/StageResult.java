package name.golets.order.hunter.common.flow;

/**
 * Marker contract for data produced by a stage and stored in flow context.
 *
 * @param <StageT> stage type that produced this result
 */
public interface StageResult<StageT extends Stage<?>> {

  /**
   * Declares the producing stage type for this result.
   *
   * @return stage class associated with this result
   */
  Class<StageT> stageType();
}
