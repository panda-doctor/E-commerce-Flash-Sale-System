import { http } from '../lib/http'
import { toId } from '../lib/id'
import type { RankEntry } from '../types/activity'

/** GET /api/rank/top10?activityId= → 抢购榜（公开，读 Redis 投影）。 */
export function fetchRank(activityId: string) {
  return http
    .get<RankEntry[]>(`/api/rank/top10?activityId=${encodeURIComponent(activityId)}`)
    .then((list) => list.map((entry) => ({ ...entry, userId: toId(entry.userId) })))
}
