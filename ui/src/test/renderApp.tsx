import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { AppProviders, AppRoutes } from '../App'

/** The whole app at `path`, with its providers; pair with `fakeApi` for the backend. */
export function renderApp(path: string) {
  return render(
    <AppProviders>
      <MemoryRouter initialEntries={[path]}>
        <AppRoutes />
      </MemoryRouter>
    </AppProviders>,
  )
}
