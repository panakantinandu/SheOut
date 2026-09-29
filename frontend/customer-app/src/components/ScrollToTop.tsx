import { useLayoutEffect } from 'react';
import { useLocation, useNavigationType } from 'react-router-dom';

/**
 * Every screen opens at its top. Without this, going forward kept the
 * scroll of the screen before it: Marketplace, opened from its card half-way
 * down Home, opened half-way down the Marketplace.
 * <p>
 * Only for a new screen (a different path). Back leaves the position alone,
 * so she returns to where she was; and a change of filters on the same
 * screen (the Marketplace's category, say) does not jump her to the top.
 */
export function ScrollToTop() {
  const { pathname } = useLocation();
  const how = useNavigationType();
  useLayoutEffect(() => {
    if (how !== 'POP') window.scrollTo({ top: 0, left: 0, behavior: 'instant' as ScrollBehavior });
  }, [pathname, how]);
  return null;
}
