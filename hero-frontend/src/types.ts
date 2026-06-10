export interface EventView {
  id: number
  title: string
  eventDateTime: string
  location: string | null
  maxCapacity: number | null
  status: string
}

export interface InviteView {
  invitationId: number
  email: string
  token: string
  rsvpLink: string
}

export interface RsvpView {
  invitationId: number
  email: string
  eventTitle: string
  status: string
  locked: boolean
}

export interface DashboardView {
  eventId: number
  eventTitle: string
  status: string
  confirmed: number
  waitlisted: number
  declined: number
  maybe: number
  noResponse: number
  maxCapacity: number | null
}

export interface CreateEventPayload {
  title: string
  description?: string
  eventDateTime: string
  location?: string
  maxCapacity?: number
}
