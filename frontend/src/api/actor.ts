const ACTOR_KEY = "sheetbridge.actor";

export function getActor(): string {
  return localStorage.getItem(ACTOR_KEY) || "maya.ops";
}

export function setActor(actor: string): void {
  localStorage.setItem(ACTOR_KEY, actor);
}
