/**
 * A notification's link, if it is a path inside this app - otherwise null.
 * <p>
 * Links come from the server, but they are followed with the router's
 * navigate(), and "//somewhere.else" or "/\somewhere.else" are paths to a
 * router and addresses to a browser: an advisory against React Router 6
 * (CVE-2025-68470) is exactly that open redirect. Only a single leading
 * slash followed by an ordinary path character gets through.
 */
export function safeInternalPath(link: string | null | undefined): string | null {
  if (!link || typeof link !== 'string') return null;
  if (!/^\/(?![/\\])[A-Za-z0-9\-._~%/?#=&]*$/.test(link)) return null;
  return link;
}
