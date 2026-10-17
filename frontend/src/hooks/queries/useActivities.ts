import { useQuery } from '@tanstack/react-query'
import { fetchActivities, fetchActivity } from '../../api/activity'

/** 活动列表（首页促销条与活动页共用），10s 过期以反映库存变化。 */
export function useActivities() {
  return useQuery({
    queryKey: ['activities'],
    queryFn: fetchActivities,
    staleTime: 10_000,
  })
}

export function useActivity(activityId: string | undefined) {
  return useQuery({
    queryKey: ['activity', activityId],
    queryFn: () => fetchActivity(activityId as string),
    enabled: Boolean(activityId),
    staleTime: 5_000,
  })
}
