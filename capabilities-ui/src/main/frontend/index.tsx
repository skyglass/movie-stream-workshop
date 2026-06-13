import { createRoot } from 'react-dom/client';
import { createBrowserRouter, RouterProvider } from 'react-router';
import { serverSideRoutes } from 'Frontend/generated/flow/Flow';
import CapabilitiesManager from 'Frontend/views/capabilities-manager';
import 'Frontend/styles/capabilities-manager.css';

const router = createBrowserRouter(
  [
    { path: '/', element: <CapabilitiesManager /> },
    { path: '/capabilities', element: <CapabilitiesManager /> },
    ...serverSideRoutes,
  ],
  { basename: new URL(document.baseURI).pathname },
);

function App() {
  return <RouterProvider router={router} />;
}

const outlet = document.getElementById('outlet')!;
const root = (outlet as any)._root ?? createRoot(outlet);
(outlet as any)._root = root;
root.render(<App />);
