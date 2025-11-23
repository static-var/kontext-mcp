import { useState, useEffect } from 'react';
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';

function App() {
  // Simple state-based routing for demo purposes
  // In a real app, use react-router-dom
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const checkAuth = async () => {
      try {
        const response = await fetch('/api/session', {
          credentials: 'include',
          headers: {
            Accept: 'application/json'
          }
        });
        setIsAuthenticated(response.ok);
      } catch (e) {
        console.error("Auth check failed", e);
        setIsAuthenticated(false);
      } finally {
        setIsLoading(false);
      }
    };
    checkAuth();
  }, []);

  const handleLogout = async () => {
    try {
      await fetch('/logout', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        credentials: 'include'
      });
    } catch (error) {
      console.error('Logout failed', error);
    } finally {
      setIsAuthenticated(false);
    }
  };

  if (isLoading) {
    return <div className="min-h-screen flex items-center justify-center bg-black text-white">Loading...</div>;
  }

  if (!isAuthenticated) {
    return <LoginPage onLogin={() => setIsAuthenticated(true)} />;
  }

  return <DashboardPage onLogout={handleLogout} />;
}

export default App;
