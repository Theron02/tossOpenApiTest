import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_client.dart';
import '../../../core/network/providers.dart';
import '../model/portfolio.dart';

abstract class PortfolioRepository {
  Future<Portfolio> getPortfolio();
}

class PortfolioRepositoryImpl implements PortfolioRepository {
  PortfolioRepositoryImpl(this._api);
  final ApiClient _api;

  @override
  Future<Portfolio> getPortfolio() async {
    final data = await _api.get('/portfolio');
    return Portfolio.fromJson(Map<String, dynamic>.from(data as Map));
  }
}

final portfolioRepositoryProvider = Provider<PortfolioRepository>(
  (ref) => PortfolioRepositoryImpl(ref.watch(apiClientProvider)),
);
