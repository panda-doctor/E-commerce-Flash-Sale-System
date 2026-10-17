import { useQuery } from '@tanstack/react-query'
import { fetchRank } from '../../api/rank'

/** 抢购榜（读 Redis 投影，公开接口）。 */
export function useRank(activityId: string | undefined) {
  return useQuery({
    queryKey: ['rank', activityId],
    queryFn: () => fetchRank(activityId as string),
    enabled: Boolean(activityId),
    staleTime: 5_000,
  })
}
