import { createBrowserRouter } from "react-router";
import SplashScreen from "./screens/SplashScreen";
import HomeDashboard from "./screens/HomeDashboard";
import LiveLocalization from "./screens/LiveLocalization";
import DestinationSearch from "./screens/DestinationSearch";
import RoutePreview from "./screens/RoutePreview";
import SensorDiagnostics from "./screens/SensorDiagnostics";
import Settings from "./screens/Settings";

export const router = createBrowserRouter([
  {
    path: "/",
    Component: SplashScreen,
  },
  {
    path: "/home",
    Component: HomeDashboard,
  },
  {
    path: "/localization",
    Component: LiveLocalization,
  },
  {
    path: "/search",
    Component: DestinationSearch,
  },
  {
    path: "/route",
    Component: RoutePreview,
  },
  {
    path: "/sensors",
    Component: SensorDiagnostics,
  },
  {
    path: "/settings",
    Component: Settings,
  },
]);
