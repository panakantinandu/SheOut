import { DesignSystemPreview } from '@sheout/design-system/preview'

// This pass is design-system only - no router yet since there are no other
// real screens to route between. App renders the token/component preview
// directly; once Login/Dashboard/New Request screens exist next pass, this
// becomes a router with /design-system as one route among others.
function App() {
  return <DesignSystemPreview />
}

export default App
