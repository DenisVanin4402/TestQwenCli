package com.example.testqwencli.dashboard;

public interface DashboardGatewayClient {

	DashboardCallOutcome callSync(DashboardGatewayRequest request);

	DashboardSubmitOutcome submitAsync(DashboardGatewayRequest request);
}
