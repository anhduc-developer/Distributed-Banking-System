import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import AppLayout from "./components/AppLayout";
import Dashboard from "./pages/Dashboard";
import Branches from "./pages/Branches";
import Customers from "./pages/Customers";
import Accounts from "./pages/Accounts";
import Transactions from "./pages/Transactions";
import Transfers from "./pages/Transfers";
import Statistics from "./pages/Statistics";
import DeadlockDemo from "./pages/DeadlockDemo";

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<AppLayout />}>
          <Route index element={<Navigate to="/dashboard" replace />} />
          <Route path="dashboard" element={<Dashboard />} />
          <Route path="branches" element={<Branches />} />
          <Route path="customers" element={<Customers />} />
          <Route path="accounts" element={<Accounts />} />
          <Route path="transactions" element={<Transactions />} />
          <Route path="transfers" element={<Transfers />} />
          <Route path="statistics" element={<Statistics />} />
          <Route path="deadlock-demo" element={<DeadlockDemo />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

export default App;
