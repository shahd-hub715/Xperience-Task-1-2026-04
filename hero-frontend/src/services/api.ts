import type { EventView, InviteView, RsvpView, DashboardView, CreateEventPayload } from '../types'

const BASE = '/api'

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })
  if (!res.ok) {
    const text = await res.text()
    throw new Error(`${res.status}: ${text}`)
  }
  return res.json() as Promise<T>
}

export function createEvent(data: CreateEventPayload): Promise<EventView> {
  return request<EventView>('/events', { method: 'POST', body: JSON.stringify(data) })
}

export function invitePerson(eventId: number, email: string): Promise<InviteView> {
  return request<InviteView>(`/events/${eventId}/invitations`, {
    method: 'POST',
    body: JSON.stringify({ email }),
  })
}

export function getRsvp(token: string): Promise<RsvpView> {
  return request<RsvpView>(`/rsvp/${token}`)
}

export function submitRsvp(token: string, response: string): Promise<RsvpView> {
  return request<RsvpView>(`/rsvp/${token}`, {
    method: 'PUT',
    body: JSON.stringify({ response }),
  })
}

export function getDashboard(eventId: number): Promise<DashboardView> {
  return request<DashboardView>(`/events/${eventId}/dashboard`)
}
